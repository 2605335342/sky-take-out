package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressMapper;

    /**
     * 新增地址
     * @param addressBook
     */
    @Override
    public void add(AddressBook addressBook) {
        //设置用户id，不为默认地址
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);

        addressMapper.insert(addressBook);
    }


    /**
     * 查询当前登录用户的所有地址信息
     * @return
     */
    @Override
    public List<AddressBook> list() {
        AddressBook addressBook = new AddressBook();
        addressBook.setUserId(BaseContext.getCurrentId());

        List<AddressBook> list=addressMapper.list(addressBook);
        return list;
    }


    /**
     * 根据id查询地址
     * @param id
     * @return
     */
    @Override
    public AddressBook getById(Long id) {
        AddressBook addressBook=addressMapper.getById(id);
        return addressBook;
    }


    /**
     * 根据id修改地址
     * @param addressBook
     */
    @Override
    public void update(AddressBook addressBook) {
        addressMapper.update(addressBook);
    }


    /**
     * 根据id删除地址
     * @param id
     */
    @Override
    public void delete(Long id) {
        addressMapper.deleteById(id);
    }


    /**
     * 设置默认地址
     * @param addressBook
     */
    @Override
    public void setDefault(AddressBook addressBook) {
        //1、将当前用户的所有地址设置为非默认地址
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressMapper.updateDefaultByUserId(addressBook);

        //2、将当前地址修改为默认地址
        addressBook.setIsDefault(1);
        addressMapper.update(addressBook);
    }


    /**
     * 查询默认地址
     * @return
     */
    @Override
    public List<AddressBook> getDefault() {
        AddressBook addressBook = new AddressBook();
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(1);

        List<AddressBook> list = addressMapper.list(addressBook);
        return list;
    }
}
